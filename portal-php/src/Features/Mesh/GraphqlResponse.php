<?php
namespace Gentics\PortalPhp\Features\Mesh;

use ArrayAccess;
use Gentics\Mesh\Client\Rest\MeshResponse;
use Serializable;
use IteratorAggregate;
use Countable;
use ArrayIterator;
use Illuminate\Support\Arr;
use Illuminate\Support\Collection;

/**
 * Class GraphqlResponse
 * @package Gentics\PortalPhp\Features\Mesh
 */
class GraphqlResponse extends Response implements ArrayAccess, Serializable, IteratorAggregate, Countable
{
    /** @var array Array representation of the MeshResponse content */
    protected $container = [];

    /**
     * GraphqlResponse constructor.
     * @param MeshResponse $data
     */
    public function __construct(MeshResponse $data)
    {
        parent::__construct($data);
        $this->container = $this->meshResponse->toArray();
    }

    /**
     * {@inheritdoc}
     * @return string
     */
    public function serialize()
    {
        return serialize($this->container);
    }

    /**
     * {@inheritdoc}
     * @param string $serialized
     */
    public function unserialize($serialized)
    {
        $this->container = unserialize($serialized);
    }

    /**
     * {@inheritdoc}
     * @return ArrayIterator|\Traversable
     */
    public function getIterator()
    {
        return new ArrayIterator($this->container);
    }

    /**
     * {@inheritdoc}
     * @return int|void
     */
    public function count()
    {
        return count($this->container);
    }

    /**
     * {@inheritdoc}
     * @return int|void
     */
    public function length()
    {
        return $this->count();
    }

    /**
     * {@inheritdoc}
     * @param mixed $offset
     * @param mixed $value
     */
    public function offsetSet($offset, $value)
    {
        if ($offset === null) {
            $this->container[] = $value;
        } else {
            $this->container[$offset] = $value;
        }
    }

    /**
     * {@inheritdoc}
     * @param mixed $offset
     * @return bool
     */
    public function offsetExists($offset)
    {
        return isset($this->container[$offset]);
    }

    /**
     * {@inheritdoc}
     * @param mixed $offset
     */
    public function offsetUnset($offset)
    {
        unset($this->container[$offset]);
    }

    /**
     * {@inheritdoc}
     * @param mixed $offset
     * @return mixed|null
     */
    public function offsetGet($offset)
    {
        return $this->container[$offset] ?? null;
    }

    /**
     * @param $key
     * @return mixed
     */
    public function __get($key)
    {
        return $this->container[$key];
    }

    /**
     * @param $key
     * @param $value
     * @return mixed
     */
    public function __set($key, $value)
    {
        return $this->container[$key] = $value;
    }

    /**
     * {@inheritdoc}
     * @param $key
     * @return bool
     */
    public function __isset($key)
    {
        return $this->offsetExists($key);
    }

    /**
     * {@inheritdoc}
     * @param $key
     */
    public function __unset($key)
    {
        $this->offsetUnset($key);
    }

    /**
     * Get an item from the response array using "dot" notation.
     *
     * @param string $key
     * @param null|mixed $default
     * @return mixed
     */
    public function get($key, $default = null)
    {
        return Arr::get($this->container, $key, $default);
    }

    /**
     * Set a response array item to a given value using "dot" notation.
     *
     * If no key is given to the method, the entire response array will be replaced.
     *
     * @param $key
     * @param $value
     */
    public function set($key, $value)
    {
        Arr::set($this->container, $key, $value);
    }

    /**
     * Check if an item or items exist in the response array using "dot" notation.
     *
     * @param $key
     * @return bool
     */
    public function has($key)
    {
        return Arr::has($this->container, $key);
    }

    /**
     * Returns response content as array
     *
     * @return array
     */
    public function toArray()
    {
        return $this->container;
    }

    /**
     * Return container or specified array keys value as Collection
     *
     * @param null|string $key
     * @param null|mixed $default
     * @return Collection
     */
    public function toCollection($key = null, $default = null)
    {
        if (!is_null($key)) {
            return new Collection($this->get($key, $default));
        } else {
            return new Collection($this->container);
        }
    }
}
